function p = s_shoot(z,w,dist)
%z = s_shoot(z,w,dist)
%   Given z, w on sphere, return the sph point which is distance 'dist'
%   (in radians) from z in direction of w. 
%   @param ctr1 (theta,phi)
%   @param ctr2 (theta,phi)
%   @param dist double (radians)

S_TOLER=.0000000000001;
if dist<S_TOLER
	p=z;
	return;
end

% adjust mod 2*pi until dist lies in [0,2*pi]
pi2=2.0*pi;
while dist<0
	dist=dist+pi2;
end
while dist>pi2
	dist =dist-pi2;
end

T=sph_tangent(z,w);
V=s_pt_to_vec(z);
A=zeros(1,3);
cosd=cos(dist);
sind=sin(dist);
A(1)=cosd*V(1)+sind*T(1);
A(2)=cosd*V(2)+sind*T(2);
A(3)=cosd*V(3)+sind*T(3);

p=proj_vec_to_sph(A(1),A(2),A(3));

end
