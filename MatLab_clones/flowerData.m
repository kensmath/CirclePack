function [u,tang,qradius,r] = flowerData(n,uzian)
%[u,qradius] = flowerData(n,uzians)
%   Compute u_{n-2} for n-flower with given uzians 
%   (length at least n-3). We compute reciprocal of
%   square roots of radii. Start with given qradius[1]
%   and qradius[2], compute qradius[j] for j=3,..,n-2.
u=0;
s=size(uzian);
len=s(2);
if len<(n-3)
    fprintf('Oops, uzian vector is too short');
    return;
end
r=zeros(1,n-2); % normalized radii
r(1)=1.0;
tang=zeros(1,n-2); % tangency points
tang(1)=0.0;
qradius=zeros(1,n-2); % reciprocal of sqrt(r)
qradius(1)=1.0;
qradius(2)=sqrt(3)*uzian(1);
r(2)=1/(qradius(2)^2);
tang(2)=2/qradius(2);
qradius(3)=3*uzian(1)*uzian(2)-1.0;
r(3)=1/(qradius(3)^2);
tang(3)=tang(2)+2/(qradius(3)*qradius(2));
for j=4:n-2
    diff=sqrt(3)*uzian(j-1)*qradius(j-1)-qradius(j-2);
    if diff<=0
        fprintf('for j=%d, diff was negative)',j);
    end
    qradius(j)=abs(diff);
    if diff<0
        tang(j)=tang(j-1)-2/(qradius(j)*qradius(j-1));
    else 
        tang(j)=tang(j-1)+2/(qradius(j)*qradius(j-1));
    end
    r(j)=1/qradius(j)^2;
end
u=(1+qradius(n-3))/(sqrt(3)*qradius(n-2));
end