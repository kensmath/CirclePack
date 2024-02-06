function T=sph_tangent(ctr1,ctr2)
% T=sph_tangent(ctr1,ctr2)
%   Given pts on sphere, return unit length 3-vector T in 
%   tangent space of first, pointing toward second. If pts are
%   essentially equal or antipodal, return 0 and set to vector
%   orthogonal to ctr1. Note that sph points are in form (theta,phi)

S_TOLER=.0000000000001;
P=zeros(1,3);	
T=zeros(1,3);	
A=s_pt_to_vec(ctr1);
B=s_pt_to_vec(ctr2);
d=A(1)*B(1)+A(2)*B(2)+A(3)*B(3);
P=[B(1)-d*A(1),B(2)-d*A(2),B(3)-d*A(3)];

% A and B essentially parallel? 
vn=norm(P,2);
if vn<S_TOLER
	pn=sqrt(A(2)*A(2)+A(3)*A(3));
	if pn>.001
		% get orthogonal, X coord 0
    	T(1)=0;
    	T(2)=A(2)/pn;
    	T(3)=-A(3)/pn;
	else
		T(1)=1;
    	T(2)=0;
    	T(3)=0;
	end
    return;
end

T=P/vn;

end